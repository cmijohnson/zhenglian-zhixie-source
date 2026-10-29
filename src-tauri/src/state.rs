use std::path::PathBuf;

use anyhow::Result;

use crate::db::Database;

#[derive(Clone)]
pub struct AppState {
    pub database: Database,
}

impl AppState {
    pub fn new(database_path: PathBuf) -> Result<Self> {
        Ok(Self {
            database: Database::new(database_path)?,
        })
    }
}
